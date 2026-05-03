# revanced-script
> [!IMPORTANT]
> Unmaintained, I dont really use the jadx UI anymore. Use https://github.com/hoo-dles/jadx-morphe
> or https://github.com/hoo-dles/jadx-revanced (unmaintained as well). If a better fork appears I'll change this.

Run ./gradlew app:dist to build the plugin then in app/build/dist you find the jar

You need the github token for accessing the revanced-patcher repo

Also sometimes shadowJar decides to not bundle everything and the plugin randomly fails for missing classes or methods, compile size should be around 100Mb

The utils module has nothing its just testing

To uninstall i believe jadx is kinda bugged, you have to uninstall the plugin from UI and then find the folder where jadx installs plugins and delete the jar there in windows is %appdata%\Roaming\skylot\jadx\config\plugins\installed


TODO: right click button to copy fingerprint

TODO: Fix jumping to nested classes not working, this is a jadx issue tho, might need to find a better way to translate short 
