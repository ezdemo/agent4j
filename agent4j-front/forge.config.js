export default {
  packagerConfig: {
    name: 'Agent4j',
    executableName: 'agent4j-front',
    appBundleId: 'com.agent4j.desktop',
    asar: true,
    asarUnpack: ['dist/**'],
  },
  makers: [
    // Windows - Squirrel installer
    {
      name: '@electron-forge/maker-squirrel',
      config: {
        name: 'Agent4j',
      },
    },
    // macOS - ZIP (可进一步转为 DMG)
    {
      name: '@electron-forge/maker-zip',
      platforms: ['darwin'],
    },
    // Linux - deb / rpm
    {
      name: '@electron-forge/maker-deb',
      config: {
        options: {
          icon: './public/favicon.png',
        },
      },
    },
    {
      name: '@electron-forge/maker-rpm',
      config: {
        options: {
          icon: './public/favicon.png',
        },
      },
    },
  ],
  plugins: [
    {
      name: '@electron-forge/plugin-vite',
      config: {
        // `build` 指定主进程和预加载脚本的入口
        build: [
          {
            entry: 'electron/main.cjs',
            config: 'vite.main.config.mjs',
          },
          {
            entry: 'electron/preload.cjs',
            config: 'vite.preload.config.mjs',
          },
        ],
        renderer: [
          {
            name: 'main_window',
            config: 'vite.renderer.config.mjs',
          },
        ],
      },
    },
  ],
}
