const esbuild = require("esbuild");
const process = require("process");
const cssModules = require("esbuild-css-modules-plugin");

esbuild
  .build({
    entryPoints: ["src/index.tsx"],
    bundle: true,
    outdir: "public",
    loader: { ".ogg": "file" },
    publicPath: "/",
    watch: process.argv[2] === "--watch",
    plugins: [cssModules()],
  })
  .catch(() => process.exit(1));
