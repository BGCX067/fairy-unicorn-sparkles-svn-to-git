javac -classpath . com/botbox/util/*.java
javac -classpath . se/sics/tac/util/*.java
javac -classpath . se/sics/tac/aw/*.java
javac -classpath . fus/agent/*.java
jar cfm tacagent.jar AWManifest.txt com/botbox/util/*.class se/sics/tac/aw/*.class se/sics/tac/util/*.class fus/agent/*.class agent.conf