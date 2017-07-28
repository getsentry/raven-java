#!/bin/bash

set -ex

[[ $TRAVIS_LANGUAGE == "cpp" ]] || (echo "Not a C++ run, exiting." && exit 0;)

pushd agent

pip install --user requests==2.18.2
find .
python agent-upload-release.py

popd