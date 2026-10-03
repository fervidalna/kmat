# Arquitectura: MVVM y Clean Architecture

La aplicación se organiza en tres capas. Cada capa depende solamente de las
capas que están más cerca de las reglas de negocio.

```text
presentation  ──>  domain  <──  data
     │                         │
 Compose y MVVM          repositorios concretos
```

## Estructura

```text
cl.kmat.ia
├── data
│   ├── repository/             Implementaciones de repositorios
│   └── AppContainer.kt         Composición de dependencias
├── domain
│   ├── model/                  Entidades puras
│   ├── repository/             Contratos de datos
│   └── usecase/                Reglas de negocio
└── presentation
    ├── common/                 Componentes reutilizables de pantalla
    ├── design/                 Tema, colores y componentes visuales
    ├── home/                   Vista de inicio
    ├── navigation/             Rutas y NavHost
    └── practice/               Vista, estado y ViewModel de práctica
```

## Responsabilidades

| Capa | Responsabilidad | No contiene |
|---|---|---|
| `presentation` | Pantallas Compose, estado visible, ViewModels y navegación. | SQL, llamadas a Supabase o reglas de persistencia. |
| `domain` | Modelos, contratos y casos de uso. | Android, Compose, Room o Supabase. |
| `data` | Implementaciones de repositorios y fuentes de datos. | Decisiones de interfaz. |

## Flujo MVVM de práctica

1. `PracticeScreen` observa `PracticeUiState` y envía las acciones del usuario.
2. `PracticeViewModel` conserva la respuesta y solicita casos de uso.
3. `GetNextExerciseUseCase` obtiene el ejercicio mediante `ExerciseRepository`.
4. `ValidateExerciseAnswerUseCase` determina si la respuesta es correcta.
5. La pantalla navega según el resultado que expone el ViewModel.

Cuando se integre Room o Supabase, se crearán implementaciones nuevas en
`data`; la capa `domain` y las pantallas no requerirán cambios por ese motivo.
