CXX ?= c++
CXXFLAGS ?= -std=c++20 -O2 -Wall -Wextra -Wpedantic
INCLUDES = -Iengine/core/include -Iengine/timeline/include -Iengine/commands/include -Iengine/project/include
SOURCES = engine/core/src/time.cpp engine/timeline/src/timeline.cpp engine/commands/src/command_history.cpp engine/project/src/project.cpp tests/unit/core_tests.cpp
.PHONY: test clean
test:
	$(CXX) $(CXXFLAGS) $(INCLUDES) $(SOURCES) -o /tmp/owncut_core_tests
	/tmp/owncut_core_tests
clean:
	rm -f /tmp/owncut_core_tests
