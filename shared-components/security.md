# Security Practices in Amazon EKS

Implementing and  maintaining security involves multiple layers, including Role-Based Access Control (RBAC), pod and node security, and secrets management. This document provides an overview of how to implement and maintain these security measures in an EKS cluster.

---

## 1. RBAC for Cluster Users: IAM and OIDC-Based Authentication

### Overview

Role-Based Access Control (RBAC) in Kubernetes allows fine-grained control over who can perform actions within the cluster. By integrating RBAC with AWS Identity and Access Management (IAM) and OpenID Connect (OIDC), EKS clusters can leverage existing AWS credentials to authenticate and authorize users.

### Implementing RBAC with IAM and OIDC

1. **Enable OIDC for EKS**: 
   - First, ensure that your EKS cluster has OIDC enabled. This allows Kubernetes to use IAM roles for service accounts (IRSA), linking IAM roles to Kubernetes service accounts.
   - You can verify and associate an OIDC provider with your EKS cluster using the AWS CLI:
     ```bash
     eksctl utils associate-iam-oidc-provider --cluster <cluster_name> --approve
     ```

2. **Create IAM Roles for Users**:
   - Define IAM roles that can be assumed by users or groups via the OIDC provider. The trust policy for these roles should allow the OIDC provider to assume the role:
     ```json
     {
       "Version": "2012-10-17",
       "Statement": [
         {
           "Effect": "Allow",
           "Principal": {
             "Federated": "arn:aws:iam::<account_id>:oidc-provider/<eks_oidc_provider>"
           },
           "Action": "sts:AssumeRoleWithWebIdentity",
           "Condition": {
             "StringEquals": {
               "<eks_oidc_provider>:sub": "system:serviceaccount:<namespace>:<service_account_name>"
             }
           }
         }
       ]
     }
     ```

3. **Map IAM Roles to Kubernetes RBAC**:
   - Use the `aws-auth` ConfigMap to map IAM roles to Kubernetes RBAC roles. For example:
     ```yaml
     mapRoles: |
       - rolearn: arn:aws:iam::<account_id>:role/<IAM_role_name>
         username: <kubernetes_username>
         groups:
           - <kubernetes_group>
     ```

4. **Assign Kubernetes Roles**:
   - Create Kubernetes roles and role bindings that define the permissions for the mapped users or groups. For example:
     ```yaml
     kind: Role
     apiVersion: rbac.authorization.k8s.io/v1
     metadata:
       namespace: default
       name: pod-reader
     rules:
     - apiGroups: [""] 
       resources: ["pods"]
       verbs: ["get", "watch", "list"]

     kind: RoleBinding
     apiVersion: rbac.authorization.k8s.io/v1
     metadata:
       name: read-pods
       namespace: default
     subjects:
     - kind: User
       name: <kubernetes_username>
       apiGroup: rbac.authorization.k8s.io
     roleRef:
       kind: Role
       name: pod-reader
       apiGroup: rbac.authorization.k8s.io
     ```

---

## 2. Pod and Node Security

### Pod Security

1. **Pod Security Policies (PSPs)**:
   - **Enforce Pod Security Standards**: PSPs define a set of conditions that a pod must meet to be accepted by the cluster. These include restrictions on running as root, controlling access to host resources, and using allowed security contexts.
   - **Example**:
     ```yaml
     apiVersion: policy/v1beta1
     kind: PodSecurityPolicy
     metadata:
       name: restricted
     spec:
       privileged: false
       runAsUser:
         rule: 'MustRunAsNonRoot'
       seLinux:
         rule: 'RunAsAny'
       supplementalGroups:
         rule: 'MustRunAs'
         ranges:
           - min: 1
             max: 65535
       fsGroup:
         rule: 'MustRunAs'
         ranges:
           - min: 1
             max: 65535
       volumes:
         - 'configMap'
         - 'emptyDir'
         - 'projected'
         - 'secret'
         - 'downwardAPI'
     ```

2. **Network Policies**:
   - Define network policies to control traffic flow between pods. For example, only allowing certain namespaces to communicate with each other or restricting ingress traffic to specific ports.

3. **Security Contexts**:
   - Define pod security contexts to enforce security at the container level, such as running containers as non-root users and setting read-only file systems.

### Node Security

1. **Node IAM Roles**:
   - Assign minimal IAM roles to worker nodes, restricting permissions to only what is necessary for the node's operations.

2. **Kubernetes Node Security Groups**:
   - Use security groups to control network traffic at the node level, ensuring that only authorized traffic can reach the nodes.

3. **Node Isolation**:
   - Isolate critical workloads by running them on dedicated nodes or using taints and tolerations to control pod placement.

---

## 3. Secrets Management

### Storing and Accessing Secrets

1. **AWS Secrets Manager**:
   -  By using AWS Secrets Manager to securely store and manage access to secrets such as database credentials, API keys, and tokens.
   - Integrate Secrets Manager with Kubernetes by using the AWS Secrets Manager and Config Provider for storing secrets directly in the Kubernetes Secrets API.

2. **Secrets Rotation**:
   - Implementing automatic secrets rotation using AWS Secrets Manager’s built-in rotation feature, which can automatically generate and rotate secrets on a predefined schedule.
   - Ensuring that applications are designed to handle secrets rotation by reloading secrets or re-establishing connections when a secret changes.

3. **Encryption**:
   - Encrypt Kubernetes secrets at rest using AWS Key Management Service (KMS) as a native EKS approach. 
   - Use IAM roles and policies to control access to secrets, ensuring that only authorized services and users can retrieve them by abiding the **principle of least privileges**.

---
