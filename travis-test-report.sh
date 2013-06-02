#!/bin/bash

sudo apt-get install -qq ftp

FTP_UPDATESITE_ROOT=/www/updatesite/branch
TESTS_DIR="${TRAVIS_BUILD_DIR}/ccw.core.test/target/surefire-reports"
PAREDIT_TESTS_DIR="${TRAVIS_BUILD_DIR}/paredit.clj/target/test-reports"
SCREENSHOTS_DIR="${TRAVIS_BUILD_DIR}/ccw.core.test/screenshots"
PADDED_TRAVIS_BUILD_NUMBER=`printf "%0*d" 6 ${TRAVIS_BUILD_NUMBER}`
UPDATESITE=ERROR-${TRAVIS_BRANCH}-travis${PADDED_TRAVIS_BUILD_NUMBER}-${ECLIPSE_TARGET}-${TRAVIS_JDK_VERSION}-git${TRAVIS_COMMIT}

ftp -pn ${FTP_HOST} <<EOF
quote USER ${FTP_USER}
quote PASS ${FTP_PASSWORD}
bin
prompt off
cd ${FTP_UPDATESITE_ROOT}
mkdir ${TRAVIS_BRANCH}
cd ${TRAVIS_BRANCH}
mkdir ${UPDATESITE}
cd ${UPDATESITE}
lcd ${TESTS_DIR}
mput *
lcd ${SCREENSHOTS_DIR}
mput *
lcd ${PAREDIT_TESTS_DIR}
mkdir paredit
cd paredit
mput *
quit
EOF
exit $?
