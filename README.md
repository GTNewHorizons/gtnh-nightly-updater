
## GTNH Nightly Updater
A tool for updating the GTNH modpack to the latest experimental/daily version via the GTNH maven.

### Requirements
Java: Version 21 or later.  
Git (optional for config updates)

### Configs
#### WARNING
**The first time you run the configs update, you will be prompted saying that the instance's configs will be replaced with the latest copy for the nightly.**  

The first time the configs are updated the following things occur:
* Modpack config repo is cloned to `.minecraft/.updater_pack_configs`
* A backup of the `.minecraft/config` folder will be copied to `.minecraft/config_backup_updater` (this only happens the first time)
* `.minecraft/config` will be deleted
* `.minecraft/.updater_pack_configs/configs` will copied to `.minecraft/config`

After that the update process will be:
* Delete `.minecraft/.updater_pack_configs/configs`
* Copy `.minecraft/config` to `.minecraft/.updater_pack_configs/configs`
* `git add .`
* `git commit -m "<auto_message>"`
* `git fetch`
* `git merge -x theirs origin/<nightly_config>`
* `.minecraft/config` will be deleted
* `.minecraft/.updater_pack_configs/configs` will copied to `.minecraft/config`

**If there are any merge conflicts, it will stop and notify the user. Those will have to done by hand**


### Usage
#### Command-Line Options
|Option| Description                                                               |  
|---|---------------------------------------------------------------------------|
|-M, --target-manifest| Required. Specify which release to update to the latest version of. (DAILY or EXPERIMENTAL) |
|-c, --configs| Optional. Only update configs (version pulled is based off the nightly manifest) |
|-C, --only-configs| Optional. Only update configs |
|--add| Required. Can be repeated. Adds an instance to updater using the below flags                    |
|-m, --minecraft| Required. Path to the target Minecraft directory.                         
| -s, --side| Required. Specify the side (CLIENT or SERVER).                            |
|-S, --symlinks| Optional. Use symlinks instead of copying mods. Mac/Linux only            |  

#### Example Command

`java -jar gtnh-nightly-updater.jar -M daily -c --add -s CLIENT -m "/mnt/games/Minecraft/Instances/GTNH_Nightly/.minecraft/" --add -s SERVER -m "/mnt/docker/appdata/minecraft/gtnh/"`

### Caching
The cache directory can be found at:  
Windows: `%LOCALAPPDATA%\gtnh-nightly-updater\`  
macOS: `~/Library/Caches/gtnh-nightly-updater/`  
Linux: `$XDG_CACHE_HOME/gtnh-nightly-updater` or `~/.cache/gtnh-nightly-updater`  

Cached mods can be found in the `mods` subdirectory.

### Local Assets and Exclusions
Local Asset File:
- File Name: local-assets.txt
- Format: MOD_NAME|SIDE
- List of mods to be included in addition to the nightly mods list. Use mod name from the maven. (Backhand|BOTH, etc) 

Exclusions:  
- File Name: mod-exclusions.txt
- Mods to be excluded from the update process (JourneyMap, etc)
