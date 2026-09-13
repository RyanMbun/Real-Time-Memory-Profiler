# Top-level Makefile. Run these from the memory-profiler/ root folder.
#
#   make build   -> builds both the C++ agent and the Java dashboard
#   make clean   -> removes both build outputs

.PHONY: build clean agent dashboard

build: agent dashboard

agent:
	$(MAKE) -C agent

dashboard:
	cd dashboard && mvn package -q

clean:
	$(MAKE) -C agent clean
	cd dashboard && mvn clean -q
