module.exports =  {
    parser:  '@typescript-eslint/parser',  // Specifies the ESLint parser
    extends:  [
      'plugin:@typescript-eslint/recommended',  // Uses the recommended rules from the @typescript-eslint/eslint-plugin
    ],
    parserOptions:  {
      ecmaVersion:  6,  // Allows for the parsing of modern ECMAScript features
      sourceType:  'script',  // Allows for the use of imports
    },
    rules:  {
        "curly": 1,
        "@typescript-eslint/explicit-function-return-type": [0],
        "@typescript-eslint/explicit-function-return-type": [0],
        "@typescript-eslint/no-explicit-any": [0],
        "@typescript-eslint/no-empty-function": [0],
        "@typescript-eslint/camelcase": "off",
        "@typescript-eslint/no-var-requires": "off",
        "@typescript-eslint/triple-slash-reference": "off",
        "ordered-imports": [0],
        "object-literal-sort-keys": [0],
        "max-len": [1, 120],
        "new-parens": 1,
        "no-bitwise": 1,
        "no-cond-assign": 1,
        "no-trailing-spaces": 0,
        "eol-last": 1,
        "semi": 1,
        "no-var": 0,
        "prefer-rest-params": 0        
    },
  };
