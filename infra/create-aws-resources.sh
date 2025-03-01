#!/usr/bin/env bash
# One-off provisioning of the DynamoDB table and S3 bucket in a real AWS account.
# RDS MySQL and the EC2 host are expected to be created separately (console/Terraform).
set -euo pipefail

TABLE="${INVENTORY_TABLE:-shelfsync-inventory}"
BUCKET="${INVOICE_BUCKET:?Set INVOICE_BUCKET to a globally unique bucket name}"
REGION="${AWS_REGION:-us-east-1}"
AWS=(aws --region "$REGION")

if ! "${AWS[@]}" dynamodb describe-table --table-name "$TABLE" >/dev/null 2>&1; then
  "${AWS[@]}" dynamodb create-table \
    --table-name "$TABLE" \
    --attribute-definitions AttributeName=sku,AttributeType=S AttributeName=category,AttributeType=S \
    --key-schema AttributeName=sku,KeyType=HASH \
    --global-secondary-indexes '[{"IndexName":"category-index","KeySchema":[{"AttributeName":"category","KeyType":"HASH"}],"Projection":{"ProjectionType":"ALL"}}]' \
    --billing-mode PAY_PER_REQUEST
  "${AWS[@]}" dynamodb update-continuous-backups --table-name "$TABLE" \
    --point-in-time-recovery-specification PointInTimeRecoveryEnabled=true
  echo "Created table $TABLE"
fi

if ! "${AWS[@]}" s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
  if [[ "$REGION" == "us-east-1" ]]; then
    "${AWS[@]}" s3api create-bucket --bucket "$BUCKET"
  else
    "${AWS[@]}" s3api create-bucket --bucket "$BUCKET" --create-bucket-configuration LocationConstraint="$REGION"
  fi
  "${AWS[@]}" s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  echo "Created bucket $BUCKET"
fi
