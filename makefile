run:
	mvn spring-boot:run
build: 
	mvn clean package -DskipTests
jar:
	git add target/sunway-1.0.0.jar.original -f
	git add target/sunway-1.0.0.jar -f 