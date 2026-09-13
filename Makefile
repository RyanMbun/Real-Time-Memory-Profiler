

.PHONY: build clean agent dashboard

build: agent dashboard

agent:
	$(MAKE) -C agent

dashboard:
	cd dashboard && mvn package -q

clean:
	$(MAKE) -C agent clean
	cd dashboard && mvn clean -q
