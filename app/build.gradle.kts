plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
}

android {
	namespace = "com.nohate.app"
	compileSdk = 34

	defaultConfig {
		applicationId = "com.nohate.app"
		minSdk = 26
		targetSdk = 34
		versionCode = 1
		versionName = "1.0"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		externalNativeBuild {
			cmake {
				cppFlags += " -std=c++17"
			}
		}

		ndk {
			abiFilters += listOf("arm64-v8a", "x86_64")
		}
	}

	externalNativeBuild {
		cmake {
			path = file("src/main/cpp/CMakeLists.txt")
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
		debug {
			isMinifyEnabled = false
		}
	}

	buildFeatures { compose = true }

	composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

	compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

	packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin { jvmToolchain(17) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { kotlinOptions { jvmTarget = "17" } }

dependencies {
	val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
	implementation(composeBom)
	androidTestImplementation(composeBom)

	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.activity:activity-compose:1.9.2")
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3:1.2.1")
	debugImplementation("androidx.compose.ui:ui-tooling")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

	implementation("androidx.work:work-runtime-ktx:2.9.1")
	implementation("androidx.security:security-crypto:1.1.0-alpha06")
	implementation("androidx.browser:browser:1.8.0")
	implementation("androidx.webkit:webkit:1.11.0")
	implementation("com.google.android.material:material:1.12.0")
	implementation("androidx.compose.material:material-icons-extended")
	implementation("androidx.navigation:navigation-compose:2.8.0")

	implementation("org.tensorflow:tensorflow-lite:2.12.0")

	androidTestImplementation("androidx.test:core-ktx:1.6.1")
	androidTestImplementation("androidx.test.ext:junit:1.2.1")
	androidTestImplementation("androidx.test:runner:1.6.2")
	androidTestImplementation("androidx.work:work-testing:2.9.1")
}
