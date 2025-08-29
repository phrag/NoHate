use jni::objects::{JClass, JString};
use jni::sys::jfloat;
use jni::JNIEnv;

#[no_mangle]
pub extern "system" fn Java_com_nohate_app_NativeClassifier_classify(
	mut env: JNIEnv,
	_class: JClass,
	input: JString,
) -> jfloat {
	let text: String = env.get_string(&input).map(|s| s.into()).unwrap_or_default();
	let normalized = text.to_lowercase();
	let patterns = ["awful", "toxic", "abuse", "hate"];
	let mut score: f32 = 0.1;
	for p in patterns.iter() {
		if normalized.contains(p) {
			score = 0.9;
			break;
		}
	}
	score as jfloat
}
