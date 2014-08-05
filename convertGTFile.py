import numpy as np
import csv

def convertGTFile(filename_in, filename_out):
    """
    Convert the exisiting ground truth files into new format.

    Existing format:
    start_time \t end_time \t context_class

    New format:
    start_time \t context_class \t "start"
    ...
    end_time \t context_class \t "end"
    """

    with open(filename_in) as f:
        reader = csv.reader(f, delimiter="\t")
        in_list = list(reader)

    unsorted_list = []
    for line in in_list:
        unsorted_list.append([float(line[0]), line[2], "start"])
        unsorted_list.append([float(line[1]), line[2], "end"])

    sorted_list = sorted(unsorted_list, key = lambda x: x)

    with open(filename_out, "wb") as f:
        writer = csv.writer(f, delimiter="\t")
        writer.writerows(sorted_list)
