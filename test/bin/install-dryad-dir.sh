#!/bin/sh

sudo mkdir -p -m 0755 /opt/dryad-blah/
sudo chown -R $USER /opt/dryad
cp -r $TRAVIS_BUILD_DIR/test/config /opt/dryad/