"""
Firebase ID token verification for the Belagavi Tourism AI backend.

Architecture:
    Android/Web client
        ↓  Firebase Authentication (login)
        ↓  Firebase issues a short-lived ID token (~1 hour)
        ↓  Client sends:  Authorization: Bearer <firebase-id-token>
    Backend (this module)
        ↓  Extracts Bearer token from Authorization header
        ↓  Calls firebase_admin.auth.verify_id_token() / google.oauth2.id_token
        ↓  Google verifies the token cryptographically
        ↓  Returns decoded token with verified uid, email, etc.
        ↓  uid is passed to the route handler (trusted)

Security rules:
    - Token must be present (401 if missing)
    - Token must be a valid Firebase ID token (401 if malformed/expired/revoked)
    - UID is ALWAYS derived from the verified token — never from a client-supplied field
    - No token value, no secret, no credential is ever logged
"""

from __future__ import annotations

import json
import logging
import os
from typing import Optional

import firebase_admin
from firebase_admin import auth, credentials
from google.auth.exceptions import DefaultCredentialsError, GoogleAuthError, MalformedError
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token as google_id_token
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

logger = logging.getLogger(__name__)

FIREBASE_PROJECT_ID = os.environ.get("FIREBASE_PROJECT_ID", "belagavi-tourism-planner")

# ---------------------------------------------------------------------------
# Firebase Admin app initialisation (singleton, lazy)
# ---------------------------------------------------------------------------

_firebase_app: Optional[firebase_admin.App] = None
_request_adapter = google_requests.Request()


def _get_firebase_app() -> firebase_admin.App:
    """
    Return the singleton Firebase Admin app, initialising it if needed.
    """
    global _firebase_app

    if _firebase_app is not None:
        return _firebase_app

    try:
        _firebase_app = firebase_admin.get_app()
        return _firebase_app
    except ValueError:
        pass

    # Option 1: inline JSON string in env var (for Vercel/serverless)
    sa_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON")
    if sa_json:
        try:
            sa_dict = json.loads(sa_json.strip())
            if isinstance(sa_dict, dict) and "private_key" in sa_dict:
                # Handle possible escaped newlines from environment variable formatting
                sa_dict["private_key"] = sa_dict["private_key"].replace("\\n", "\n")
            cred = credentials.Certificate(sa_dict)
            _firebase_app = firebase_admin.initialize_app(cred)
            logger.info("Firebase Admin initialised from FIREBASE_SERVICE_ACCOUNT_JSON")
            return _firebase_app
        except Exception as exc:
            logger.error("Failed to parse FIREBASE_SERVICE_ACCOUNT_JSON: %s", type(exc).__name__)

    # Option 2: GOOGLE_APPLICATION_CREDENTIALS or Application Default Credentials
    try:
        cred = credentials.ApplicationDefault()
        _firebase_app = firebase_admin.initialize_app(cred)
        logger.info("Firebase Admin initialised from Application Default Credentials")
        return _firebase_app
    except Exception:
        pass

    # Option 3: Initialise with project ID for public cert verification
    try:
        _firebase_app = firebase_admin.initialize_app(options={"projectId": FIREBASE_PROJECT_ID})
        logger.info("Firebase Admin initialised with project ID")
        return _firebase_app
    except Exception as exc:
        logger.warning("Could not initialize Firebase Admin SDK app: %s", type(exc).__name__)

    return None


def verify_token_payload(token: str) -> dict:
    """
    Verify a Firebase ID token and return decoded claims.
    Rejects missing, empty, malformed, expired, invalid, and revoked tokens.
    """
    if not token or not token.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token is missing. Please log in again.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    app = _get_firebase_app()

    # 1. Try Firebase Admin verify_id_token with revocation checking
    if app is not None:
        try:
            return auth.verify_id_token(token, app=app, check_revoked=True)
        except auth.RevokedIdTokenError:
            logger.warning("Auth rejected: revoked Firebase ID token")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Your session has been revoked. Please log in again.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        except auth.ExpiredIdTokenError:
            logger.warning("Auth rejected: expired Firebase ID token")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Your session has expired. Please log in again.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        except auth.InvalidIdTokenError as exc:
            logger.warning("Auth rejected: invalid Firebase ID token (%s)", type(exc).__name__)
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid authentication token. Please log in again.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        except (DefaultCredentialsError, ValueError) as exc:
            # If service account is not configured on this machine, check without check_revoked
            # or use google.oauth2.id_token against Google public keys
            logger.debug("Falling back to public cert verification: %s", type(exc).__name__)
        except HTTPException:
            raise
        except Exception as exc:
            logger.warning("Firebase Admin verification failed: %s", type(exc).__name__)

    # 2. Cryptographic verification against Google's public certs (standard Firebase RS256 JWT)
    try:
        decoded = google_id_token.verify_firebase_token(
            token,
            _request_adapter,
            audience=FIREBASE_PROJECT_ID,
        )
        if decoded:
            return decoded
    except MalformedError:
        logger.warning("Auth rejected: malformed token structure")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Malformed authentication token.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except GoogleAuthError as exc:
        err_msg = str(exc).lower()
        if "expired" in err_msg:
            logger.warning("Auth rejected: expired token")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Your session has expired. Please log in again.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        logger.warning("Auth rejected: GoogleAuthError (%s)", type(exc).__name__)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token. Please log in again.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except Exception as exc:
        err_name = type(exc).__name__
        logger.warning("Auth rejected: token verification failed (%s)", err_name)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication failed. Please log in again.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    raise HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid authentication token. Please log in again.",
        headers={"WWW-Authenticate": "Bearer"},
    )


# ---------------------------------------------------------------------------
# FastAPI dependency — requires a valid Firebase Bearer token
# ---------------------------------------------------------------------------

_bearer_scheme = HTTPBearer(auto_error=False)


async def require_firebase_user(
    credentials_header: Optional[HTTPAuthorizationCredentials] = Depends(_bearer_scheme),
) -> str:
    """
    FastAPI dependency that verifies the Firebase ID token and returns the verified uid.

    Raises:
        HTTPException(401): if token is missing, malformed, expired, invalid, or revoked.
    """
    if credentials_header is None:
        logger.warning("Auth rejected: missing Authorization header")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required. Please log in and try again.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = credentials_header.credentials
    decoded = verify_token_payload(token)

    uid: Optional[str] = decoded.get("uid") or decoded.get("user_id") or decoded.get("sub")
    if not uid:
        logger.warning("Auth rejected: token payload missing uid")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token claims.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return str(uid)
