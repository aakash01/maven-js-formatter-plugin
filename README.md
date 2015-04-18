# maven-js-formatter-plugin
maven-js-formatter-plugin

It is a maven plugin to format javascript code using JSBeautifier. 

# Usage 
Sample pom file 

```xml
<plugin>
				<groupId>com.github.maven-js-formatter-plugin</groupId>
				<artifactId>maven-js-formatter-plugin</artifactId>
				<version>0.1</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.maven</groupId>
						<artifactId>maven-plugin-api</artifactId>
						<version>2.0</version>
					</dependency>
				</dependencies>
				<executions>
					<execution>
						<phase>compile</phase>
						<goals>
							<goal>format</goal>
						</goals>
						<configuration>
							<directories>
								<param>${project.build.jsSourceDirectory}</param>
							</directories>
						</configuration>
					</execution>
				</executions>
			</plugin>
			
		<properties>
        		<project.build.jsSourceDirectory>${project.basedir}/src/main/webapp/scripts/app/view</project.build.jsSourceDirectory>
        	</properties>
```


#Credits
The code is developed using -

1. https://github.com/beautify-web/js-beautify/blob/master/js/lib/beautify.js  (MIT license , jsbeautifier.org)

2. https://code.google.com/p/maven-java-formatter-plugin/ - Apache License 2.0

3. https://code.google.com/p/jsplugins/ - Apache License 2.0
