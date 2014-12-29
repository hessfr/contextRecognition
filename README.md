Summary
==================
This interactive audio-based context recognition system for mobile phones was developed as a part of my master's thesis at ETH Zurich. In contrast to traditional machine learning approaches, we used
crowd-sourced audio data to train our classification model and adapt it to the user’s
context, instead of training purely on user data.

Therefore the initial training for each (new) context class is performed on a server back end while the real-time prediction and the adaptation to the user-specific context is done on the mobile phone itself.

In order to adapt the crowd-sourced model to the user-specific context, we use stream-based active learning to ask the user for labels of difficult-to-classify samples.

For the evaluation of this concept, seven participants used the context recognition system on four consecutive days and logged their actual context classes. Overall more than 286 hours of audio data was recorded and classified in up to 13 different
context classes.

An overall average accuracy of 48.7% was achieved during the course of the user study. This performance is significantly better compared to a purely crowd-sourced model, which resulted in an average accuracy of 28.7% on the same data set, confirming the feasibility of the proposed system.
