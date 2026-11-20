.PHONY: test test-c test-java test-locales clean

test: test-c test-java
	@echo "=============================================================="
	@echo "SUITE AISLADA COMPLETA (RQ-03): C + 3 MICROSERVICIOS JAVA OK"
	@echo "=============================================================="

test-c:
	$(MAKE) -C native-lib test

test-java:
	mvn -f services/calculo-service/pom.xml test
	mvn -f services/insumo-service/pom.xml test
	mvn -f services/presupuesto-service/pom.xml test

test-locales:
	mvn -f services/calculo-service/pom.xml test -DargLine="-Duser.language=es -Duser.country=CO"
	mvn -f services/calculo-service/pom.xml test -DargLine="-Duser.language=en -Duser.country=US"

clean:
	$(MAKE) -C native-lib clean
