import os
import json
from dotenv import load_dotenv
from flask import Flask, render_template, redirect, url_for, request, flash, jsonify, make_response
from flask_login import LoginManager, login_user, login_required, logout_user, current_user
from werkzeug.security import generate_password_hash, check_password_hash
from flask_wtf.csrf import CSRFProtect, generate_csrf
from models import db, User, Place, Wishlist, Expense

load_dotenv()

app = Flask(__name__)

# --- Security Configuration ---
secret_key = os.environ.get('SECRET_KEY')
if not secret_key:
    import warnings
    warnings.warn(
        "SECRET_KEY is not set. Using an insecure default — set it via .env or environment variable.",
        stacklevel=2
    )
    secret_key = 'insecure-default-do-not-use-in-production'

app.config['SECRET_KEY'] = secret_key

db_url = os.environ.get('DATABASE_URL', 'sqlite:///belagavi.db')
if db_url.startswith("postgres://"):
    db_url = db_url.replace("postgres://", "postgresql://", 1)

app.config['SQLALCHEMY_DATABASE_URI'] = db_url

db.init_app(app)
csrf = CSRFProtect(app)

# Inject CSRF token into all templates
@app.after_request
def set_csrf_cookie(response):
    response.set_cookie('csrf_token', generate_csrf())
    return response

from sqlalchemy import text
from data import PLACE_DETAILS

# --- Admin credentials from environment ---
ADMIN_USERNAME = os.environ.get('ADMIN_USERNAME', 'Yuvaraj')
ADMIN_EMAIL    = os.environ.get('ADMIN_EMAIL', 'yuvaraj@gmail.com')
ADMIN_PASSWORD = os.environ.get('ADMIN_PASSWORD', 'Yuvaraj1718')

with app.app_context():
    db.create_all()

    # Migrate: extend password column
    try:
        db.session.execute(text('ALTER TABLE "user" ALTER COLUMN password TYPE VARCHAR(255);'))
        db.session.commit()
    except Exception:
        db.session.rollback()

    # Migrate: add is_admin column
    try:
        db.session.execute(text('ALTER TABLE "user" ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;'))
        db.session.commit()
    except Exception:
        db.session.rollback()

    # Migrate: add firebase_uid column
    try:
        db.session.execute(text('ALTER TABLE "user" ADD COLUMN firebase_uid VARCHAR(128);'))
        db.session.commit()
    except Exception:
        db.session.rollback()

    # Migrate: add transport_json column to place
    try:
        db.session.execute(text('ALTER TABLE place ADD COLUMN transport_json TEXT;'))
        db.session.commit()
    except Exception:
        db.session.rollback()

    # Seed places on first run
    if not Place.query.first():
        for folder_name, details in PLACE_DETAILS.items():
            new_place = Place(
                name=details.get('name'),
                folder_name=folder_name,
                category=details.get('category'),
                description=(details.get('history') or '')[:200],
                history=details.get('history'),
                architecture=details.get('architecture'),
                famous_features=details.get('famous_features'),
                lat=details.get('lat'),
                lon=details.get('lon'),
                best_time=details.get('best_time'),
                entry_fee=details.get('entry_fee'),
                visit_duration=details.get('visit_duration'),
                city=details.get('city'),
                how_to_reach=details.get('how_to_reach'),
                local_tips=details.get('local_tips'),
                detailed_history=details.get('detailed_history'),
                transport_json=json.dumps(details.get('transport')) if details.get('transport') else None
            )
            db.session.add(new_place)
    else:
        # Update transport_json for existing places that have it empty
        for folder_name, details in PLACE_DETAILS.items():
            if details.get('transport'):
                place = Place.query.filter_by(folder_name=folder_name).first()
                if place and not place.transport_json:
                    place.transport_json = json.dumps(details.get('transport'))

    # Seed admin user on first run
    admin = User.query.filter_by(username=ADMIN_USERNAME).first()
    if not admin:
        hashed_pw = generate_password_hash(ADMIN_PASSWORD, method='scrypt')
        admin = User(
            username=ADMIN_USERNAME,
            email=ADMIN_EMAIL,
            password=hashed_pw,
            is_admin=True
        )
        db.session.add(admin)
    elif not admin.is_admin:
        # Ensure existing admin user has the flag set
        admin.is_admin = True

    db.session.commit()

login_manager = LoginManager(app)
login_manager.login_view = 'login'

# --- Google Maps + Firebase config available in all templates ---
@app.context_processor
def inject_maps_key():
    return {
        'google_maps_key': os.environ.get('GOOGLE_MAPS_JS_KEY', ''),
        'firebase_api_key': os.environ.get('FIREBASE_API_KEY', ''),
        'firebase_auth_domain': os.environ.get('FIREBASE_AUTH_DOMAIN', ''),
        'firebase_project_id': os.environ.get('FIREBASE_PROJECT_ID', ''),
        'firebase_storage_bucket': os.environ.get('FIREBASE_STORAGE_BUCKET', ''),
        'firebase_messaging_sender_id': os.environ.get('FIREBASE_MESSAGING_SENDER_ID', ''),
        'firebase_app_id': os.environ.get('FIREBASE_APP_ID', ''),
    }

# --- Firebase Admin SDK ---
# Supports two modes:
#   1. Local dev:  FIREBASE_CREDENTIALS_PATH = path to serviceAccountKey.json
#   2. Production: FIREBASE_CREDENTIALS_JSON = base64-encoded JSON string (Cloud Run env var)
_firebase_initialized = False
def get_firebase_app():
    global _firebase_initialized
    if _firebase_initialized:
        return True
    try:
        import firebase_admin
        from firebase_admin import credentials

        # Mode 2: JSON string in env var (Cloud Run / production)
        creds_json_b64 = os.environ.get('FIREBASE_CREDENTIALS_JSON', '')
        if creds_json_b64:
            import base64
            creds_dict = json.loads(base64.b64decode(creds_json_b64).decode('utf-8'))
            cred = credentials.Certificate(creds_dict)
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            return True

        # Mode 1: credentials file (local dev)
        creds_path = os.environ.get('FIREBASE_CREDENTIALS_PATH', 'serviceAccountKey.json')
        if os.path.exists(creds_path):
            cred = credentials.Certificate(creds_path)
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            return True

    except Exception as e:
        print(f'Firebase init failed: {e}')
    return False


def _safe_internal_path(path):
    """Allow only same-site relative redirects."""
    return bool(path) and isinstance(path, str) and path.startswith('/') and not path.startswith('//')


@login_manager.user_loader
def load_user(user_id):
    return db.session.get(User, int(user_id))


# ---------------------------------------------------------------------------
# Public routes
# ---------------------------------------------------------------------------

@app.route('/')
def welcome():
    if current_user.is_authenticated:
        return redirect(url_for('home'))
    return render_template('welcome.html')


@app.route('/planner')
@login_required
def home():
    featured_names = ['jalavane_falls', 'kamalbasadi_belagavi', 'vidhansoudha_belagavi', 'chorla_ghat', 'yellur_fort', 'vajrapoha_falls']
    places = Place.query.filter(Place.folder_name.in_(featured_names)).all()
    all_places = Place.query.all()
    user_wishlist = Wishlist.query.filter_by(user_id=current_user.id).all()
    return render_template('home.html', places=places, all_places_list=all_places, user_wishlist=user_wishlist)


@app.route('/login', methods=['GET', 'POST'])
def login():
    if current_user.is_authenticated:
        return redirect(url_for('home'))
    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        password = request.form.get('password', '')
        user = User.query.filter_by(username=username).first()
        if user and check_password_hash(user.password, password):
            login_user(user)
            next_url = request.args.get('next')
            if _safe_internal_path(next_url):
                return redirect(next_url)
            return redirect(url_for('home'))
        flash('Invalid username or password')
    return render_template('login.html')


@app.route('/register', methods=['GET', 'POST'])
def register():
    if current_user.is_authenticated:
        return redirect(url_for('home'))
    if request.method == 'POST':
        username = request.form.get('username', '').strip()
        email = request.form.get('email', '').strip()
        password = request.form.get('password', '')
        if not username or not email or not password:
            flash('All fields are required.')
            return redirect(url_for('register'))

        if User.query.filter_by(username=username).first():
            flash('Username is already taken.')
            return redirect(url_for('register'))

        if User.query.filter_by(email=email).first():
            flash('Email is already registered. Please log in instead.')
            return redirect(url_for('login'))

        hashed_pw = generate_password_hash(password, method='scrypt')
        new_user = User(username=username, email=email, password=hashed_pw, is_admin=False)
        db.session.add(new_user)
        db.session.commit()
        return redirect(url_for('login'))
    return render_template('register.html')


# ---------------------------------------------------------------------------
# Place routes
# ---------------------------------------------------------------------------

@app.route('/place/<int:place_id>')
@login_required
def place_details(place_id):
    place = db.get_or_404(Place, place_id)

    image_folder = os.path.join(app.root_path, 'static', 'images', place.folder_name)
    images = []
    if os.path.exists(image_folder):
        images = sorted(
            (f for f in os.listdir(image_folder) if f.lower().endswith(('.jpg', '.png', '.jpeg')))
        )

    recommended_places = (
        Place.query.filter(Place.id != place.id, Place.category == place.category)
        .order_by(Place.name).limit(4).all()
    )

    # Parse transport JSON for the template
    transport = None
    if place.transport_json:
        try:
            transport = json.loads(place.transport_json)
        except Exception:
            transport = None

    return render_template(
        'details.html',
        place=place,
        images=images,
        recommended_places=recommended_places,
        transport=transport,
    )


@app.route('/navigate/<int:place_id>')
@login_required
def navigate(place_id):
    place = db.get_or_404(Place, place_id)
    return render_template('navigate.html', place=place)


@app.route('/api/directions')
@login_required
def directions_proxy():
    """Server-side proxy for Google Directions API — keeps key off the client."""
    import urllib.request as urlreq
    origin = request.args.get('origin', '').strip()
    destination = request.args.get('destination', '').strip()
    if not origin or not destination:
        return jsonify({'error': 'Missing origin or destination'}), 400
    key = os.environ.get('GOOGLE_MAPS_DIRECTIONS_KEY', '')
    if not key:
        return jsonify({'error': 'Directions API key not configured'}), 500
    url = (
        f'https://maps.googleapis.com/maps/api/directions/json'
        f'?origin={origin}&destination={destination}'
        f'&mode=driving&departure_time=now&traffic_model=best_guess'
        f'&key={key}'
    )
    try:
        with urlreq.urlopen(url, timeout=10) as resp:
            data = resp.read()
        return data, 200, {'Content-Type': 'application/json'}
    except Exception as e:
        return jsonify({'error': str(e), 'status': 'FETCH_ERROR'}), 500


# ---------------------------------------------------------------------------
# Firebase Auth endpoint
# ---------------------------------------------------------------------------

@app.route('/auth/firebase', methods=['POST'])
@csrf.exempt  # Firebase ID token is its own proof of identity
def firebase_auth():
    """Verify a Firebase ID token and create a Flask-Login session."""
    if not get_firebase_app():
        return jsonify({'error': 'Firebase not configured on server'}), 503

    from firebase_admin import auth as firebase_auth_module
    data = request.get_json(silent=True) or {}
    id_token = data.get('idToken', '')

    if not id_token:
        return jsonify({'error': 'No token provided'}), 400

    try:
        decoded = firebase_auth_module.verify_id_token(id_token)
    except Exception as e:
        return jsonify({'error': f'Invalid token: {e}'}), 401

    uid = decoded['uid']
    email = decoded.get('email', '')
    display_name = decoded.get('name', email.split('@')[0] if email else f'user_{uid[:8]}')

    # Find or create user
    user = User.query.filter_by(firebase_uid=uid).first()
    if not user and email:
        user = User.query.filter_by(email=email).first()
    if not user:
        # Create new user from Firebase profile
        user = User(
            username=display_name,
            email=email or f'{uid}@firebase.local',
            password=generate_password_hash(os.urandom(24).hex(), method='scrypt'),
            firebase_uid=uid,
            is_admin=False
        )
        db.session.add(user)
        db.session.commit()
    elif not user.firebase_uid:
        user.firebase_uid = uid
        db.session.commit()

    login_user(user)
    return jsonify({'status': 'ok', 'redirect': url_for('home')})


@app.route('/sw.js')
def sw():
    response = make_response(app.send_static_file('sw.js'))
    response.headers['Content-Type'] = 'application/javascript'
    return response


# ---------------------------------------------------------------------------
# Admin routes — protected by is_admin flag
# ---------------------------------------------------------------------------

@app.route('/admin')
@login_required
def admin_dashboard():
    if not current_user.is_admin:
        flash('Access Denied. Admins only.')
        return redirect(url_for('home'))
    all_places = Place.query.all()
    return render_template('admin.html', places=all_places)


@app.route('/admin/edit/<int:place_id>', methods=['POST'])
@login_required
def admin_edit(place_id):
    if not current_user.is_admin:
        return redirect(url_for('home'))
    place = db.get_or_404(Place, place_id)
    place.name = request.form.get('name', place.name)
    place.category = request.form.get('category', place.category)
    place.description = request.form.get('description', place.description)
    db.session.commit()
    flash(f'{place.name} updated successfully!')
    return redirect(url_for('admin_dashboard'))


@app.route('/admin/add', methods=['POST'])
@login_required
def admin_add():
    if not current_user.is_admin:
        return redirect(url_for('home'))
    new_place = Place(
        name=request.form.get('name', 'New Place'),
        folder_name=request.form.get('folder_name', 'default'),
        category=request.form.get('category', 'Local Discovery'),
        description=request.form.get('description', '')
    )
    db.session.add(new_place)
    db.session.commit()
    flash(f'{new_place.name} added successfully!')
    return redirect(url_for('admin_dashboard'))


@app.route('/admin/delete/<int:place_id>', methods=['POST'])
@login_required
def admin_delete(place_id):
    if not current_user.is_admin:
        return redirect(url_for('home'))
    place = db.get_or_404(Place, place_id)
    Wishlist.query.filter_by(place_id=place.id).delete()
    db.session.delete(place)
    db.session.commit()
    flash(f'{place.name} deleted successfully!')
    return redirect(url_for('admin_dashboard'))


# ---------------------------------------------------------------------------
# User data APIs
# ---------------------------------------------------------------------------

@app.route('/add_to_wishlist/<int:place_id>', methods=['POST'])
@login_required
@csrf.exempt  # JSON API — called from JS fetch()
def add_to_wishlist(place_id):
    exists = Wishlist.query.filter_by(user_id=current_user.id, place_id=place_id).first()
    if not exists:
        db.session.add(Wishlist(user_id=current_user.id, place_id=place_id))
        db.session.commit()
    return jsonify({"status": "success"})


@app.route('/remove_from_wishlist/<int:place_id>', methods=['POST'])
@login_required
@csrf.exempt  # JSON API — called from JS fetch()
def remove_from_wishlist(place_id):
    item = Wishlist.query.filter_by(user_id=current_user.id, place_id=place_id).first()
    if item:
        db.session.delete(item)
        db.session.commit()
    return jsonify({"status": "success"})


@app.route('/api/expenses', methods=['GET', 'POST'])
@login_required
@csrf.exempt  # JSON API — called from JS fetch()
def manage_expenses():
    if request.method == 'GET':
        expenses = Expense.query.filter_by(user_id=current_user.id).order_by(Expense.id.desc()).all()
        return jsonify([{
            'id': e.id,
            'location': e.location,
            'name': e.name,
            'amount': e.amount,
            'category': e.category,
            'date': e.date
        } for e in expenses])

    if request.method == 'POST':
        data = request.get_json(silent=True)
        if not data:
            return jsonify({'error': 'Invalid JSON payload'}), 400

        location = data.get('location', '').strip()
        amount = data.get('amount')
        category = data.get('category', 'Misc').strip()

        # Server-side validation
        if not location:
            return jsonify({'error': 'Location is required'}), 422
        try:
            amount = float(amount)
            if amount <= 0:
                raise ValueError
        except (TypeError, ValueError):
            return jsonify({'error': 'Amount must be a positive number'}), 422
        if category not in ('Transport', 'Food', 'Entry Fee', 'Stay', 'Misc'):
            category = 'Misc'

        new_exp = Expense(
            user_id=current_user.id,
            location=location,
            name=data.get('name', '').strip(),
            amount=amount,
            category=category,
            date=data.get('date', '')
        )
        db.session.add(new_exp)
        db.session.commit()
        return jsonify({'status': 'success', 'id': new_exp.id})


@app.route('/api/expenses/<int:expense_id>', methods=['DELETE'])
@login_required
@csrf.exempt  # JSON API — called from JS fetch()
def delete_expense(expense_id):
    expense = Expense.query.filter_by(id=expense_id, user_id=current_user.id).first()
    if expense:
        db.session.delete(expense)
        db.session.commit()
        return jsonify({'status': 'success'})
    return jsonify({'error': 'Not found'}), 404


@app.route('/api/expenses/clear', methods=['POST'])
@login_required
@csrf.exempt  # JSON API — called from JS fetch()
def clear_expenses():
    Expense.query.filter_by(user_id=current_user.id).delete()
    db.session.commit()
    return jsonify({'status': 'success'})


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

@app.route('/logout')
@login_required
def logout():
    logout_user()
    return redirect(url_for('welcome'))


if __name__ == '__main__':
    with app.app_context():
        db.create_all()
    app.run(debug=True)