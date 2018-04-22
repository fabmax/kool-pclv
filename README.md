# kool-pclv
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/fabmax/kool-pclv/blob/master/LICENSE)

A [Google Cartographer](https://github.com/googlecartographer) compatible web-based point cloud viewer
based on [kool](https://github.com/fabmax/kool).

Serve a point cloud from a PLY file or a Cartographer on-disk octree and view it in your browser. For now
this does more or less the same as the original
[Cartographer point cloud viewer](https://github.com/googlecartographer/point_cloud_viewer) but there's more stuff
to come.

## Building and Running

Build the project with:
```
./gradlew dist
```

Then start the server with
```
java -jar ./dist/pclv-0.0.1.jar -d [path to pointcloud data]
```

The server opens up a port on 8080 (can be changed with -p [port]). Open
[http://localhost:8080](http://localhost:8080) with the browser of your choice and start viewing point clouds!

### Point cloud data
Currently only ply-Files and [Cartographer](https://github.com/googlecartographer) compatible octrees
are supported. ply-Files are loaded entirely into memory so they should not be too large. Octrees are streamed from
disk on demand so they can be of more or less arbitrary size ('billions of points'). However generation of octrees is
not yet supported so you need the 
[Cartographer point cloud viewer](https://github.com/googlecartographer/point_cloud_viewer)
to generate them.

