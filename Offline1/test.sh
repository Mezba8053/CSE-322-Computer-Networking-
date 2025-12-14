#!/bin/bash
# filepath: /home/mezba/Downloads/CSE 322/test_concurrent.sh

echo "Starting 5 concurrent clients..."

for i in {1..5}
do
    echo "Starting client $i"
    (
        echo "user$i"
        echo "yes"
        sleep 1
        echo "3"  # Upload
        echo "test$i.txt"
        echo "public"
        sleep 2
        echo "9"  # Exit
    ) | java Offline1.client &
done

wait
echo "All clients finished"