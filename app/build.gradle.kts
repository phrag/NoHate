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

	buildFeatures {
		compose = true
	}

	composeOptions {
		kotlinCompilerExtensionVersion = "1.5.14"
	}

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

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
}
