#!/usr/bin/env bash
# Builds the invoice Lambda and creates or updates the function.
#
#   ./infra/deploy-lambda.sh            # real AWS (needs LAMBDA_ROLE_ARN on first deploy)
#   ./infra/deploy-lambda.sh --local    # LocalStack on http://localhost:4566
#
# Env: AWS_REGION, INVOICE_BUCKET, INVOICE_LAMBDA_FUNCTION, LAMBDA_ROLE_ARN
set -euo pipefail

cd "$(dirname "$0")/.."

FUNCTION="${INVOICE_LAMBDA_FUNCTION:-shelfsync-invoice-generator}"
BUCKET="${INVOICE_BUCKET:-shelfsync-invoices}"
REGION="${AWS_REGION:-us-east-1}"
HANDLER="com.shelfsync.invoice.lambda.InvoiceHandler::handleRequest"
JAR="invoice-lambda/target/invoice-lambda.jar"

AWS=(aws --region "$REGION")
ENV_VARS="Variables={INVOICE_BUCKET=$BUCKET}"
if [[ "${1:-}" == "--local" ]]; then
  AWS+=(--endpoint-url http://localhost:4566)
  export AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test
  LAMBDA_ROLE_ARN="arn:aws:iam::000000000000:role/lambda-role"
  # Inside LocalStack the Lambda container reaches LocalStack via its hostname.
  ENV_VARS="Variables={INVOICE_BUCKET=$BUCKET,AWS_ENDPOINT_OVERRIDE=http://localstack:4566}"
fi

echo "Building $JAR"
mvn -q -B -pl invoice-lambda -am -DskipTests package

if "${AWS[@]}" lambda get-function --function-name "$FUNCTION" >/dev/null 2>&1; then
  echo "Updating $FUNCTION"
  "${AWS[@]}" lambda update-function-code --function-name "$FUNCTION" --zip-file "fileb://$JAR" >/dev/null
  "${AWS[@]}" lambda wait function-updated --function-name "$FUNCTION"
  "${AWS[@]}" lambda update-function-configuration --function-name "$FUNCTION" \
    --environment "$ENV_VARS" >/dev/null
else
  : "${LAMBDA_ROLE_ARN:?Set LAMBDA_ROLE_ARN to an IAM role with s3:PutObject on the invoice bucket}"
  echo "Creating $FUNCTION"
  "${AWS[@]}" lambda create-function \
    --function-name "$FUNCTION" \
    --runtime java17 \
    --architectures arm64 \
    --memory-size 1024 \
    --timeout 30 \
    --handler "$HANDLER" \
    --role "$LAMBDA_ROLE_ARN" \
    --zip-file "fileb://$JAR" \
    --environment "$ENV_VARS" >/dev/null
fi

echo "Done: $FUNCTION"
