package cl.kmat.ia.data

import android.content.Context
import androidx.room.Room
import cl.kmat.ia.data.auth.SecureSessionStore
import cl.kmat.ia.data.auth.SupabaseAuthRepository
import cl.kmat.ia.data.admin.SupabaseManagedAccountsRepository
import cl.kmat.ia.data.local.OfflineDatabase
import cl.kmat.ia.data.local.OfflineDatabaseMigrations
import cl.kmat.ia.data.repository.InMemoryExerciseRepository
import cl.kmat.ia.data.repository.RoomLearningSessionRepository
import cl.kmat.ia.data.sync.LearningSyncScheduler
import cl.kmat.ia.data.sync.OfflineSyncCoordinator
import cl.kmat.ia.data.sync.SupabaseSyncClient
import cl.kmat.ia.data.student.SupabaseStudentProfilesRepository
import cl.kmat.ia.data.teacher.SupabaseTeachingRepository
import cl.kmat.ia.domain.repository.ExerciseRepository
import cl.kmat.ia.domain.repository.AuthRepository
import cl.kmat.ia.domain.repository.LearningSessionRepository
import cl.kmat.ia.domain.repository.TeachingRepository
import cl.kmat.ia.domain.usecase.GetDailyOfflineExerciseUseCase
import cl.kmat.ia.domain.usecase.GetStudentProgressUseCase
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
    val offlineDatabase: OfflineDatabase = Room.databaseBuilder(
        applicationContext,
        OfflineDatabase::class.java,
        "kmat-offline.db"
    ).addMigrations(
        OfflineDatabaseMigrations.MIGRATION_1_2,
        OfflineDatabaseMigrations.MIGRATION_2_3,
        OfflineDatabaseMigrations.MIGRATION_3_4
    ).build()
    val studentProfilesRepository = SupabaseStudentProfilesRepository(authRepository, offlineDatabase)
    val managedAccountsRepository = SupabaseManagedAccountsRepository(authRepository)
    val teachingRepository: TeachingRepository = SupabaseTeachingRepository(authRepository)

    val exerciseRepository: ExerciseRepository = InMemoryExerciseRepository()
    val getNextExercise = GetNextExerciseUseCase(exerciseRepository)
    val validateExerciseAnswer = ValidateExerciseAnswerUseCase()

    private val syncClient = SupabaseSyncClient(authRepository)
    val syncCoordinator = OfflineSyncCoordinator(offlineDatabase, syncClient)
    val learningSessionRepository: LearningSessionRepository = RoomLearningSessionRepository(
        offlineDatabase,
        applicationContext,
        downloadRemoteProgress = { syncClient.downloadProgress() },
        downloadTeacherAssignments = { studentId -> syncClient.downloadTeacherAssignments(studentId) }
    )
    val getDailyOfflineExercise = GetDailyOfflineExerciseUseCase(learningSessionRepository)
    val getStudentProgress = GetStudentProgressUseCase(learningSessionRepository)
    val savePracticeAttempt = SavePracticeAttemptUseCase(learningSessionRepository)
    init {
        LearningSyncScheduler.schedulePeriodic(applicationContext)
    }
}
