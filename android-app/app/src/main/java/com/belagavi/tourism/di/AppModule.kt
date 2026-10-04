package com.belagavi.tourism.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(firebaseAuth: FirebaseAuth): okhttp3.OkHttpClient {
        // Firebase ID token interceptor — attaches the current user's token to AI API requests.
        // The token is fetched synchronously (OkHttp interceptors run on a background thread).
        // If no user is signed in, the request is sent without a token; the backend returns 401,
        // which the existing error handler in AiRepository surfaces as a user-facing message.
        val firebaseAuthInterceptor = Interceptor { chain ->
            val original = chain.request()
            val currentUser = firebaseAuth.currentUser
            val request = if (currentUser != null) {
                // getIdToken(false) returns the cached token if still valid, or refreshes it.
                // We call the blocking form inside the OkHttp thread pool — safe here.
                val token: String? = try {
                    com.google.android.gms.tasks.Tasks.await(
                        currentUser.getIdToken(false)
                    )?.token
                } catch (e: Exception) {
                    null  // network/auth error — proceed without token, backend will 401
                }
                if (token != null) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    original
                }
            } else {
                original
            }
            chain.proceed(request)
        }

        val builder = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(firebaseAuthInterceptor)

        val logging = okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC  // no body in release
        }
        builder.addInterceptor(logging)

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideAiApiService(okHttpClient: okhttp3.OkHttpClient): com.belagavi.tourism.data.network.AiApiService {
        return retrofit2.Retrofit.Builder()
            .baseUrl(com.belagavi.tourism.data.network.AiApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.belagavi.tourism.data.network.AiApiService::class.java)
    }
}
