pipeline {
    agent any

    environment {
        DOCKER_HUB_USERNAME = 'anujop'
        IMAGE_TAG = 'latest'
        // This links to the credentials you will create in Jenkins
        DOCKER_CREDS = credentials('docker-hub-credentials')
    }

    stages {
        stage('Build') {
            steps {
                echo '🚀 Building Backend Image...'
                sh 'docker build -t ${DOCKER_HUB_USERNAME}/cfarena-backend:${IMAGE_TAG} ./Backend'
                
                echo '🚀 Building Frontend Image...'
                // The build args are hardcoded here because they are public API URLs, not secrets
                sh '''
                docker build \
                  --build-arg VITE_API_URL=https://cfarena.in \
                  --build-arg VITE_WS_URL=https://cfarena.in \
                  --build-arg VITE_GOOGLE_CLIENT_ID=746507303760-6op2ba78jjkakt9a47gqb040u0e301v6.apps.googleusercontent.com \
                  -t ${DOCKER_HUB_USERNAME}/cfarena-frontend:${IMAGE_TAG} \
                  ./myapp
                '''
            }
        }
        
        stage('Push to Docker Hub') {
            steps {
                echo '🐳 Logging into Docker Hub...'
                sh 'echo $DOCKER_CREDS_PSW | docker login -u $DOCKER_CREDS_USR --password-stdin'
                
                echo '☁️ Pushing Images...'
                sh 'docker push ${DOCKER_HUB_USERNAME}/cfarena-backend:${IMAGE_TAG}'
                sh 'docker push ${DOCKER_HUB_USERNAME}/cfarena-frontend:${IMAGE_TAG}'
            }
        }
        
        stage('Deploy to Live Server') {
            steps {
                echo '⚡ Deploying to EC2...'
                // We run this inside the mounted /opt/cfarena directory so it has access to the .env file!
                dir('/opt/cfarena') {
                    sh 'docker compose pull'
                    sh 'docker compose up -d --force-recreate'
                }
            }
        }
    }

}
