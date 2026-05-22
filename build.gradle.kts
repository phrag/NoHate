plugins {
	id("com.android.application") version "8.11.1" apply false
	kotlin("android") version "1.9.24" apply false
	// Pin to 1.3.4: Paparazzi 1.3.5 ships Kotlin 2.0 on its plugin classpath,
	// which makes AGP demand the new Compose Compiler Gradle plugin even though
	// the project itself is still on Kotlin 1.9.24. Revisit when we migrate
	// the codebase to Kotlin 2.0.
	id("app.cash.paparazzi") version "1.3.4" apply false
}
