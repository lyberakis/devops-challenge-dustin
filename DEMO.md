# EKS and Argo CD demo

Deploy the application to EKS Auto Mode using GitHub Actions, GHCR, and Argo CD.

```text
App change → build → test + security scan → publish to GHCR → commit image tag
                                                                    ↓
                                                       Argo CD → EKS
Chart change → Helm validation; Argo CD applies the chart from Git
```

Run the shell commands below in AWS CloudShell unless explicitly marked local. Each command is on a single line. AWS infrastructure incurs charges while it exists.

## 1. Create the EKS cluster

1. Open EKS and select **Create cluster**.
2. Select **Quick configuration (with EKS Auto Mode)**.
3. Set the name to `devops-challenge-dustin` and choose a Kubernetes version in standard support.
4. Use the role-creation buttons to create the recommended Auto Mode roles, then refresh and select them:
   - Cluster: `AmazonEKSAutoClusterRole`.
   - Nodes: `AmazonEKSAutoNodeRole`.
5. Select a VPC with three subnets across different availability zones.
6. Create the cluster and wait for **Active**.

## 2. Install Argo CD via Cloudshell

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl rollout status deployment/argocd-server -n argocd --timeout=600s
kubectl get pods -n argocd
```

Retrieve the initial admin password:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
```

### Browser access

For the public browser access used in this demo, run this in **CloudShell**:

```bash
kubectl get svc argocd-server -n argocd
```

When `EXTERNAL-IP` shows an AWS hostname, press Ctrl+C and open `https://<hostname>`.

# 3. Enable the ingress class

In Cloudshell, run:
```bash
git clone https://github.com/lyberakis/devops-challenge-dustin.git
cd devops-challenge-dustin
kubectl apply -f infrastructure/eks/alb-ingressclass.yaml
```

This will give an IP Address from AWS with a unique DNS hostname. 

## 4. Create the applications on ArgoCD

There is a parent Application, defined in `infrastructure/argocd/argocd.yaml`, to watch `infrastructure/argocd/applications/`. The child manifests `test.yaml` and `production.yaml` manage `hello-test` and `hello-production`.

Apply the following once:
```bash
git pull
kubectl apply -f infrastructure/argocd/argocd.yaml
```

## 5. Get the application address

```bash
kubectl get ingress -n hello-<environment>
```

When `ADDRESS` appears, press Ctrl+C. Obtain and test the hostname by opening `http://<ADDRESS>/` in your browser.

## 6. Create Sealed Secrets

Install helm and kubeseal:

```bash
curl -fsSL -o /tmp/get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
bash /tmp/get_helm.sh
curl -fL -o /tmp/kubeseal.tar.gz https://github.com/bitnami/sealed-secrets/releases/download/v0.41.2/kubeseal-0.41.2-linux-amd64.tar.gz
tar -xzf /tmp/kubeseal.tar.gz -C /tmp kubeseal
sudo install -m 755 /tmp/kubeseal /usr/local/bin/kubeseal
```

Install sealed secret:

```bash
helm repo add sealed-secrets https://bitnami.github.io/sealed-secrets
helm repo update
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets --namespace sealed-secrets --create-namespace --set fullnameOverride=sealed-secrets-controller --wait --timeout 5m
```

Seal a secret:

```bash
kubeseal --controller-name=sealed-secrets-controller --controller-namespace=sealed-secrets --fetch-cert > sealed-secrets.pem
read -rsp 'API key: ' APP_API_KEY
printf '%s' "$APP_API_KEY" | kubeseal --raw --scope strict --cert sealed-secrets.pem --namespace hello-<environment> --name hello-world-secrets
unset APP_API_KEY
```
