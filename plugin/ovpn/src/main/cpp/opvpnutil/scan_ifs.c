#include <jni.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <linux/if.h>
#include <android/log.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <errno.h>

#include "jniglue.h"

jobjectArray Java_de_blinkt_openvpn_core_NativeUtils_getIfconfig(JNIEnv* env)
{
    int sd;
    if ((sd = socket (AF_INET, SOCK_DGRAM, 0)) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "Opening socket for intface get failed");
        //jniThrowException(env, "java/lang/IllegalArgumentException", "Opening socket for intface get failed");
        return NULL;
    }

    struct ifreq ifs[23];

    struct ifconf ifc;  
    ifc.ifc_req = ifs;
    ifc.ifc_len = sizeof (ifs);

    if (ioctl (sd, SIOCGIFCONF, &ifc) < 0) {
        __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "IOCTL for intface get failed");
        //jniThrowException(env, "java/lang/IllegalArgumentException", "IOTCL socket for intface get failed");
        close(sd);
        return NULL;
    }
    

    

    char buf[NI_MAXHOST];
    
    int ji=0;
    
    /*
    jtmp = (*env)->NewStringUTF(env, "HALLO WELT");
    (*env)->SetObjectArrayElement(env, ret, ji++, jtmp);
    */

    size_t num_intf=ifc.ifc_len / sizeof(struct ifreq);
    jobjectArray ret= (jobjectArray) (*env)->NewObjectArray(env, num_intf*3,(*env)->FindClass(env, "java/lang/String"), NULL);

    for (struct ifreq* ifr = ifc.ifc_req; ifr <   ifs + num_intf; ifr++) {
        
        if (ifr->ifr_addr.sa_family != AF_INET)  {
            __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "NOT AF_INET: %s", ifr->ifr_name);
            continue;
        }
        
        /* get interface addr, prefilled by SIOGIFCONF */
        
        int err;
        if ((err=getnameinfo(&ifr->ifr_addr, sizeof(struct sockaddr_in), buf, NI_MAXHOST, NULL, 0,
                             NI_NUMERICHOST)) !=0) {
            __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "getnameinfo failed for  %s: %s", ifr->ifr_name,  gai_strerror(err));
            continue;
        }
        jstring jaddr = (*env)->NewStringUTF(env, buf);
        jstring jname = (*env)->NewStringUTF(env, ifr->ifr_name);
            

        struct ifreq ifreq;
        strncpy (ifreq.ifr_name, ifr->ifr_name, sizeof (ifreq.ifr_name));

        /* interface is up */
        if (ioctl (sd, SIOCGIFFLAGS, &ifreq) < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "SIOCGIFFLAGS failed for %s: %s", ifr->ifr_name, strerror(errno));
            continue;
        }
        
        if (!(ifreq.ifr_flags & IFF_UP)) {
            __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "IFF_UP failed for %s", ifr->ifr_name);
            continue;
        }

        /* interface netmask */
        if (ioctl (sd, SIOCGIFNETMASK, &ifreq) < 0) {
            __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "SIOCIFNETMASK failed for %s: %s", ifr->ifr_name, strerror(errno));
            continue;
        }
       
        if ((err=getnameinfo(&ifreq.ifr_netmask, sizeof(struct sockaddr_in), buf, NI_MAXHOST, NULL, 0,
                             NI_NUMERICHOST)) !=0) {
            __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "getnameinfo failed for  %s: %s", ifr->ifr_name,  gai_strerror(err));
            continue;
        }
        jstring jnetmask = (*env)->NewStringUTF(env, buf);
        
        (*env)->SetObjectArrayElement(env, ret, ji++, jname);
        (*env)->SetObjectArrayElement(env, ret, ji++, jaddr);
        (*env)->SetObjectArrayElement(env, ret, ji++, jnetmask);
    }
    if (sd >= 0)
        close (sd);
    
    return ret;
}

