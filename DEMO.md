# EKS and Argo CD demo

Here are the steps to deploy the application to EKS Auto Mode using GitHub Actions, GHCR, and Argo CD.

Run the shell commands below in AWS CloudShell.

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
kubectl patch svc argocd-server -n argocd --type merge -p '{"metadata":{"annotations":{"service.beta.kubernetes.io/aws-load-balancer-scheme":"internet-facing","service.beta.kubernetes.io/aws-load-balancer-nlb-target-type":"ip","service.beta.kubernetes.io/aws-load-balancer-attributes":"load_balancing.cross_zone.enabled=true"}},"spec":{"type":"LoadBalancer","loadBalancerClass":"eks.amazonaws.com/nlb"}}'
kubectl get svc argocd-server -n argocd
```

Open `https://<hostname>` to see the ArgoCD UI.

# 3. Enable the ingress class

In Cloudshell, run:
```bash
git clone https://github.com/lyberakis/devops-challenge-dustin.git
cd devops-challenge-dustin
kubectl apply -f infrastructure/eks/alb-ingressclass.yaml
```

This configures the shared IngressClass. AWS provisions a load balancer and assigns it a DNS hostname when Argo CD deploys the application’s Ingress.

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

Get the hostname by opening `http://<ADDRESS>/` in your browser.

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
