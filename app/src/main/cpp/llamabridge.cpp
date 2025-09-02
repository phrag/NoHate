#include <jni.h>
#include <string>
#include <mutex>

// llama.cpp headers
#include "llama.h"

static std::mutex g_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nohate_app_llm_LlamaBridge_loadModel(
	JNIEnv* env,
	jclass,
	jstring jpath
) {
	std::lock_guard<std::mutex> lock(g_mutex);
	if (g_ctx != nullptr) return JNI_TRUE;
	const char* cpath = env->GetStringUTFChars(jpath, nullptr);
	std::string path = cpath ? std::string(cpath) : std::string();
	if (cpath) env->ReleaseStringUTFChars(jpath, cpath);
	if (path.empty()) return JNI_FALSE;

	llama_backend_init();
	llama_model_params mparams = llama_model_default_params();
	g_model = llama_model_load_from_file(path.c_str(), mparams);
	if (!g_model) return JNI_FALSE;
	llama_context_params cparams = llama_context_default_params();
	cparams.n_ctx = 1024;
	g_ctx = llama_init_from_model(g_model, cparams);
	if (!g_ctx) {
		llama_model_free(g_model); g_model = nullptr;
		return JNI_FALSE;
	}
	return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nohate_app_llm_LlamaBridge_classify(
	JNIEnv* env,
	jclass,
	jstring jtext,
	jstring jprompt
) {
	std::lock_guard<std::mutex> lock(g_mutex);
	// Placeholder until full inference wired
	const char* ctext = env->GetStringUTFChars(jtext, nullptr);
	std::string text = ctext ? std::string(ctext) : std::string();
	if (ctext) env->ReleaseStringUTFChars(jtext, ctext);
	bool hate = false;
	std::string lower = text;
	for (auto& ch : lower) ch = (char)tolower((unsigned char)ch);
	if (lower.find("fuck") != std::string::npos || lower.find("kill") != std::string::npos || lower.find("hate") != std::string::npos) hate = true;
	std::string out = std::string("{\"label\":\"") + (hate ? "hate" : "not_hate") + "\",\"score\":" + (hate ? "0.85" : "0.15") + "}";
	return env->NewStringUTF(out.c_str());
}

