provider "aws" {
  region = "us-west-2"
}

# VPC
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"
  enable_dns_support = true
  enable_dns_hostnames = true
  tags = {
    Name = "my-vpc"
  }
}

# Subnets
resource "aws_subnet" "subnet_a" {
  vpc_id     = aws_vpc.main.id
  cidr_block  = "10.0.1.0/24"
  availability_zone = "us-west-2a"
  tags = {
    Name = "my-subnet-a"
  }
}

resource "aws_subnet" "subnet_b" {
  vpc_id     = aws_vpc.main.id
  cidr_block  = "10.0.2.0/24"
  availability_zone = "us-west-2b"
  tags = {
    Name = "my-subnet-b"
  }
}

resource "aws_subnet" "subnet_c" {
  vpc_id     = aws_vpc.main.id
  cidr_block  = "10.0.3.0/24"
  availability_zone = "us-west-2c"
  tags = {
    Name = "my-subnet-c"
  }
}

# Security Group
resource "aws_security_group" "eks" {
  vpc_id = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "eks-sg"
  }
}

# IAM Roles
resource "aws_iam_role" "eks" {
  name = "eks-cluster-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "eks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks" {
  role       = aws_iam_role.eks.name
  policy_arn  = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role" "eks_node" {
  name = "eks-node-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eks_node" {
  role       = aws_iam_role.eks_node.name
  policy_arn  = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "eks_node_cni" {
  role       = aws_iam_role.eks_node.name
  policy_arn  = "arn:aws:iam::aws:policy/AmazonEKSCNIPolicy"
}

# ECR
resource "aws_ecr_repository" "my_repo" {
  name = "my-ecr-repo"
}

# RDS PostgreSQL
resource "aws_db_instance" "postgres" {
  allocated_storage    = 100
  storage_type         = "gp2"
  engine               = "postgres"
  engine_version       = "13.3"
  instance_class       = "db.m5.large"
  name                 = "mydb"
  username             = "dbuser"
  password             = "dbpassword"
  parameter_group_name = "default.postgres13"
  multi_az             = true
  backup_retention_period = 7
  vpc_security_group_ids = [aws_security_group.eks.id]
  db_subnet_group_name  = aws_db_subnet_group.default.name
  tags = {
    Name = "my-rds-instance"
  }
}

resource "aws_db_subnet_group" "default" {
  name       = "default"
  subnet_ids = [aws_subnet.subnet_a.id, aws_subnet.subnet_b.id, aws_subnet.subnet_c.id]
}

# EKS Cluster
resource "aws_eks_cluster" "my_cluster" {
  name     = "my-cluster"
  role_arn  = aws_iam_role.eks.arn

  vpc_config {
    subnet_ids = [aws_subnet.subnet_a.id, aws_subnet.subnet_b.id, aws_subnet.subnet_c.id]
    security_group_ids = [aws_security_group.eks.id]
  }
}

# Output
output "cluster_endpoint" {
  value = aws_eks_cluster.my_cluster.endpoint
}

output "cluster_name" {
  value = aws_eks_cluster.my_cluster.name
}

output "ecr_repo_url" {
  value = aws_ecr_repository.my_repo.repository_url
}
