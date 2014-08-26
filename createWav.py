import numpy as np
import os
import math
import wave
import struct
import ipdb as pdb

def createWav(path, binary_file, output_file, sample_rate=16000, datatype=np.int16):
    """

    """
    fin = open(os.path.join(path, binary_file), "rb")

    fin.seek(0, os.SEEK_END)
    file_length = fin.tell()
    len_seconds = fin.tell() / (sample_rate * 2)

    stepsize_seconds = 600
    stepsize = int(sample_rate * stepsize_seconds)
    
    n_iter = int(math.ceil(len_seconds/float(stepsize_seconds)))

    output_file = output_file + ".wav"
    fout = wave.open(os.path.join(path, output_file), 'wb')
    fout.setparams((1, 2, sample_rate, 0, 'NONE', 'not compressed'))
    
    for i in range(n_iter):

        offset_seconds = i*stepsize_seconds
        offset = int(sample_rate * 2 * offset_seconds)
        fin.seek(offset, os.SEEK_SET)

        data = np.fromfile(fin, datatype, stepsize)

        data_size = data.shape[0]

        data_struct = struct.pack('%sh' % data_size, *data)
        fout.writeframes(data_struct)

    fout.close()














