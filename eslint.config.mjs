import js from "@eslint/js";
import globals from "globals";
import pluginReact from "eslint-plugin-react";
import { defineConfig, globalIgnores } from "eslint/config";


export default defineConfig([
  globalIgnores([
    '**/*.config.js',
    '**/target/**'
  ]),
  {
    settings: {
      react: {
        version: "17"
      }
    }
  },
  { files: ["**/*.{js,mjs,cjs,jsx}"], plugins: { js }, extends: ["js/recommended"] },
  {
    files: ["**/*.{js,mjs,cjs,jsx}"],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.jest,
        module: "readonly",
        global: "readonly",
        Ext: "readonly",
        NX: "readonly",
        process: "readonly"
      }
    }
  },
  pluginReact.configs.flat.recommended,
  pluginReact.configs.flat['jsx-runtime'],
  {
    rules: {
      "react/jsx-uses-react": "error",
      "react/prop-types": "off",
      "no-useless-escape": "off",
      "no-extra-boolean-cast": "off",
      "no-unused-vars": ["error", { "argsIgnorePattern": "^_" }]
    }
  }
]);
