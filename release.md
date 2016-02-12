#to release a new version:

- gradle clean uploadArchives

- goto https://oss.sonatype.org/#stagingRepositories
  login with your credentials, check artifacts.
  After your deployment the repository will be in an Open status. 
  You can evaluate the deployed components in the repository using the Contents tab. 
  If you believe everything is correct you, can press the Close button above the list. 
  This will trigger the evaluations of the components against the requirements. 
  
- or execute: gradle closeAndPromoteRepository