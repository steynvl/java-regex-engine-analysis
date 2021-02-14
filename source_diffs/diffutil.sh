#!/bin/bash

# https://unix.stackexchange.com/questions/17040/how-to-diff-files-ignoring-comments-lines-starting-with
diff -u -B <(grep -vE '^\s*(#|$)' $1) <(grep -vE '^\s*(#|$)' $2)

