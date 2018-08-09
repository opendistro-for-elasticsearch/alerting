Amazon Elasticsearch Service custom plugins for Elasticsearch

## Developer Setup (Intellij IDEA)

1. If you're on OSX download and install JDK 10 (or higher) from the oracle download site. If you're on Amazon Linux use Amazon's OpenJDK build: https://w.amazon.com/index.php/JDKTeam/OpenJDK 
1. Checkout this package from version control. 
1. Launch Intellij IDEA, Choose Import Project and select the `settings.gradle` file in the root of this package. 
1. Select the Java 10 SDK you downloaded in Step 1 as the Project JDK. 
1. In 'Global Gradle Settings' -> 'Gradle VM options' add the line `-Didea.active=true`
1. Import the project. 
