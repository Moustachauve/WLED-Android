╔═ getChangelog ignores non-version files such as README md ═╗
[VersionChangelog(version=Version 7.0.1, content=Added new feature X)]
╔═ getChangelog includes dev md for beta releases ═╗
[VersionChangelog(version=Version Dev, content=Bleeding edge updates), VersionChangelog(version=Version 7.0.1, content=Stable release 7.0.1)]
╔═ getChangelog includes dev md when isDebugBuild is true ═╗
[VersionChangelog(version=Version Dev, content=Dev changes), VersionChangelog(version=Version 7.0.0, content=Stable 7.0.0)]
╔═ getChangelog returns relevant versions newer than last seen ═╗
[VersionChangelog(version=Version 7.0.2, content=Fixed bug Y), VersionChangelog(version=Version 7.0.1, content=Added new feature X)]
╔═ [end of file] ═╗
