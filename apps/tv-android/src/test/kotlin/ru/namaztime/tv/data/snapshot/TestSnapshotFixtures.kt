package ru.namaztime.tv.data.snapshot

import java.io.File

internal fun syntheticSnapshotBytes(): ByteArray =
    File("../../examples/synthetic-prayer-snapshot.json").readBytes()

internal fun syntheticSnapshotAssetSource(): SnapshotAssetSource =
    SnapshotAssetSource(::syntheticSnapshotBytes)
