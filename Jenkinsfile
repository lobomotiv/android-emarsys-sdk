@Library(['general-pipeline']) _

node('master') {
    lock(env.ANDROID_EMARSYS_SDK_BUILD) {
        withSlack channel: 'jenkins', {
            timeout(30) {
                stage('init') {
                    deleteDir()
                    git url: 'git@github.com:emartech/android-emarsys-sdk.git', branch: 'master'
                }
                stage('core') {
                    build job: 'android-core-sdk'
                }
                stage('mobile-engage') {
                    build job: 'android-mobile-engage-sdk'
                }
                stage('sample') {
                    build job: 'android-emarsys-sdk-sample'
                }
            }
        }
    }
}