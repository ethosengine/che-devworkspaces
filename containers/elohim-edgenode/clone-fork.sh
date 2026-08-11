#!/bin/sh
# clone-fork.sh — fetch one fork into the builder, by exact commit when known.
#
#   clone-fork.sh <url> <branch> <ref> <dest>
#
# <ref> (a commit SHA) wins over <branch> when non-empty: the elohim monorepo
# passes the SHA its submodule pointer records, so the built binary corresponds
# to committed source rather than a branch tip that moved between builds.
# Empty <ref> keeps the historical branch-tip clone for standalone builds.
#
# Lives as a script (not an inline RUN) because the branch/ref conditional is
# the kind of shell that is unreadable folded into a Dockerfile line.
set -eu

url="$1"
branch="$2"
ref="$3"
dest="$4"

if [ -n "$ref" ]; then
    echo "── ${dest}: fetching exact commit ${ref} from ${url}"
    git init -q "$dest"
    git -C "$dest" remote add origin "$url"
    # Shallow fetch of a bare SHA — GitHub allows this (allowAnySHA1InWant).
    git -C "$dest" fetch -q --depth 1 origin "$ref"
    git -C "$dest" checkout -q FETCH_HEAD
else
    echo "── ${dest}: cloning branch tip ${branch} from ${url}"
    git clone -q --depth 1 -b "$branch" "$url" "$dest"
fi

echo "── ${dest}: built from $(git -C "$dest" rev-parse HEAD)"
