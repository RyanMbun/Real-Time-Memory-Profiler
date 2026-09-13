# Makefile for the C++ agent.
#
# Instead of typing this every time:
#   g++ -std=c++17 -Wall -o profiler src/main.cpp src/proc_reader.cpp src/socket_sender.cpp
# you just type: make
#
# "make" looks for a file called Makefile, finds the "all" target (the
# first one), and runs its recipe. Recipe lines MUST start with a real TAB
# character, not spaces, or make will error out.

CXX = g++
CXXFLAGS = -std=c++17 -Wall -O2
SOURCES = src/main.cpp src/proc_reader.cpp src/socket_sender.cpp
TARGET = profiler

all: $(TARGET)

$(TARGET): $(SOURCES)
	$(CXX) $(CXXFLAGS) -o $(TARGET) $(SOURCES)

clean:
	rm -f $(TARGET)

.PHONY: all clean
