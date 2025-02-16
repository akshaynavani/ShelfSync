#!/usr/bin/env bash
# Runs inside LocalStack once it is ready: creates the DynamoDB table and the S3 bucket.
set -euo pipefail

TABLE="${INVENTORY_TABLE:-shelfsync-inventory}"
BUCKET="${INVOICE_BUCKET:-shelfsync-invoices}"

if ! awslocal dynamodb describe-table --table-name "$TABLE" >/dev/null 2>&1; then
  awslocal dynamodb create-table \
    --table-name "$TABLE" \
    --attribute-definitions AttributeName=sku,AttributeType=S AttributeName=category,AttributeType=S \
    --key-schema AttributeName=sku,KeyType=HASH \
    --global-secondary-indexes '[{"IndexName":"category-index","KeySchema":[{"AttributeName":"category","KeyType":"HASH"}],"Projection":{"ProjectionType":"ALL"}}]' \
    --billing-mode PAY_PER_REQUEST
  echo "Created DynamoDB table $TABLE"
fi

if ! awslocal s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
  awslocal s3 mb "s3://$BUCKET"
  echo "Created S3 bucket $BUCKET"
fi
