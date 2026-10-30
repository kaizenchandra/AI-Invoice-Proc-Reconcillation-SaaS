#!/bin/sh
set -eu
awslocal s3 mb s3://invoices
awslocal s3api put-public-access-block --bucket invoices --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
