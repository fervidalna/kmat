package cl.kmat.ia.data

import android.content.Context
import androidx.room.Room
import cl.kmat.ia.data.auth.SecureSessionStore
import cl.kmat.ia.data.auth.SupabaseAuthRepository
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.data.repository.InMemoryExerciseRepository
import cl.kmat.ia.data.repository.RoomLearningSessionRepository
import cl.kmat.ia.data.sync.LearningSyncScheduler
import cl.kmat.ia.data.sync.OfflineSyncCoordinator
import cl.kmat.ia.data.sync.SupabaseSyncClient
import cl.kmat.ia.domain.repository.ExerciseRepository
import cl.kmat.ia.domain.repository.AuthRepository
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.GetNextExerciseUseCase
import cl.kmat.ia.domain.usecase.SavePracticeAttemptUseCase
import cl.kmat.ia.domain.usecase.SignInUseCase
import cl.kmat.ia.domain.usecase.SignUpUseCase
import cl.kmat.ia.domain.usecase.RequestPasswordResetUseCase
import cl.kmat.ia.domain.usecase.ValidateExerciseAnswerUseCase

/**
 * Punto único de composición de dependencias.
 * Room y Supabase reemplazarán estas implementaciones sin afectar la capa UI.
 */
class AppContainer(applicationContext: Context) {
    val authRepository: AuthRepository = SupabaseAuthRepository(SecureSessionStore(applicationContext))
    val signIn = SignInUseCase(authRepository)
    val signUp = SignUpUseCase(authRepository)
    val requestPasswordReset = RequestPasswordResetUseCase(authRepository)

    val exerciseRepository: ExerciseRepository = InMemoryExerciseRepository()
    val getNextExercise = GetNextExerciseUseCase(exerciseRepository)
    val validateExerciseAnswer = ValidateExerciseAnswerUseCase()

    val offlineDatabase: OfflineDatabase = Room.databaseBuilder(
        applicationContext,
        OfflineDatabase::class.java,
        "kmat-offline.db"
    ).build()
    val learningSessionRepository: LearningSessionRepository = RoomLearningSessionRepository(
        offlineDatabase,
        applicationContext
    )
    val getDailyOfflineExercise = GetDailyOfflineExerciseUseCase(learningSessionRepository)
    val savePracticeAttempt = SavePracticeAttemptUseCase(learningSessionRepository)
    val syncCoordinator = OfflineSyncCoordinator(
        offlineDatabase,
        SupabaseSyncClient(authRepository)
    )

    init {
        LearningSyncScheduler.schedulePeriodic(applicationContext)
    }
}
