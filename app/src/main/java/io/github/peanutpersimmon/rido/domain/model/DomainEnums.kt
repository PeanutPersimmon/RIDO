package io.github.peanutpersimmon.rido.domain.model

enum class TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    CANCELLED,
}

enum class TaskCompletionSource {
    MANUAL,
    CHILDREN,
}

enum class SessionKind {
    ACTIVITY,
    NIGHT_SLEEP,
    NAP,
}

enum class SessionTemporalType {
    POINT,
    INTERVAL,
}

