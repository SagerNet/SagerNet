//
//  JBCyrpto.cpp
//  xcopenvpn
//
//  Created by Arne Schwabe on 12.07.12.
//  Copyright (c) 2012 Universität Paderborn. All rights reserved.
//

#include <jni.h>

#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <dlfcn.h>

// Minimal defines for openssl 1.0.x
typedef void *RSA;

struct EVP_PKEY
{
  int type;
  int save_type;
  int references;
  void *ameth;
  void *engine;
  union {
    RSA *rsa;
  } pkey;
};

#define RSA_PKCS1_PADDING       1
#define RSA_NO_PADDING		3

extern "C" {
    jbyteArray Java_de_blinkt_openvpn_core_NativeUtils_rsasign(JNIEnv* env, jclass, jbyteArray from, jint pkeyRef, jboolean pkcs1padding);
    int jniThrowException(JNIEnv* env, const char* className, const char* msg);

    int (*RSA_size_dyn)(const RSA *);
    int (*RSA_private_encrypt_dyn)(int, const unsigned char *, unsigned char *, RSA *, int);

    unsigned long (*ERR_get_error_dyn)();
    void (*ERR_error_string_n_dyn)(unsigned long, char *, size_t);

    void (*ERR_print_errors_fp_dyn)(FILE *);

}

int jniThrowException(JNIEnv* env, const char* className, const char* msg) {

    jclass exceptionClass = env->FindClass(className);

    if (exceptionClass == NULL) {
        __android_log_print(ANDROID_LOG_DEBUG,"openvpn","Unable to find exception class %s", className);
        /* ClassNotFoundException now pending */
        return -1;
    }

    if (env->ThrowNew( exceptionClass, msg) != JNI_OK) {
    	__android_log_print(ANDROID_LOG_DEBUG,"openvpn","Failed throwing '%s' '%s'", className, msg);
        /* an exception, most likely OOM, will now be pending */
        return -1;
    }

    env->DeleteLocalRef(exceptionClass);
    return 0;
}

static char opensslerr[1024];
jbyteArray Java_de_blinkt_openvpn_core_NativeUtils_rsasign (JNIEnv* env, jclass, jbyteArray from, jint pkeyRef, jboolean pkcs1padding) {


	//	EVP_MD_CTX* ctx = reinterpret_cast<EVP_MD_CTX*>(ctxRef);
	EVP_PKEY* pkey = reinterpret_cast<EVP_PKEY*>(pkeyRef);


	if (pkey == NULL || from == NULL) {
		jniThrowException(env, "java/lang/NullPointerException", "EVP_KEY is null");
		return NULL;
	}

	jbyte* data =  env-> GetByteArrayElements (from, NULL);
	int  datalen = env-> GetArrayLength(from);

	if(data==NULL )
		jniThrowException(env, "java/lang/NullPointerException", "data is null");

    int siglen;
    RSA_size_dyn= (int (*) (const RSA *)) dlsym(RTLD_DEFAULT, "RSA_size");
	unsigned char* sigret = (unsigned char*)malloc(RSA_size_dyn(pkey->pkey.rsa));


	//int RSA_sign(int type, const unsigned char *m, unsigned int m_len,
	//           unsigned char *sigret, unsigned int *siglen, RSA *rsa);

	// adapted from s3_clnt.c
    /*	if (RSA_sign(NID_md5_sha1, (unsigned char*) data, datalen,
        sigret, &siglen, pkey->pkey.rsa) <= 0 ) */

    RSA_private_encrypt_dyn=(int (*)(int, const unsigned char *, unsigned char *, RSA *, int)) dlsym(RTLD_DEFAULT, "RSA_private_encrypt");
    int paddding = pkcs1padding ? RSA_PKCS1_PADDING : RSA_NO_PADDING;
    siglen = RSA_private_encrypt_dyn(datalen,(unsigned char*) data,sigret,pkey->pkey.rsa, paddding);

    if (siglen < 0)
	{
        ERR_get_error_dyn = (unsigned long (*)()) dlsym(RTLD_DEFAULT, "ERR_get_error");
        ERR_error_string_n_dyn = (void (*)(unsigned long, char *, size_t)) dlsym(RTLD_DEFAULT, "ERR_error_string_n");

        ERR_error_string_n_dyn(ERR_get_error_dyn(), opensslerr ,1024);
		jniThrowException(env, "java/security/InvalidKeyException", opensslerr);

        ERR_print_errors_fp_dyn = (void (*)(FILE *)) dlsym(RTLD_DEFAULT, "ERR_print_errors_fp");
		ERR_print_errors_fp_dyn(stderr);
		return NULL;

	}


	jbyteArray jb;

	jb =env->NewByteArray(siglen);

	env->SetByteArrayRegion(jb, 0, siglen, (jbyte *) sigret);
	free(sigret);
	return jb;

}
