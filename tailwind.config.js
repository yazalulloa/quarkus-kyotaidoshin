/**    "tw-elements": "1.1.0" */

/** @type {import('tailwindcss').Config} */

module.exports = {
  // darkMode: 'class',
  darkMode: 'media',
  content: [
    "./src/main/resources/**/*.{html,js,ts}",
    "./frontend/**/*.{html,js,ts,css}",
  ],
  plugins: [
    require("@tailwindcss/typography"),
    // require("daisyui")
  ],
  theme: {
    screens: {
      'sm': '640px',
      'md': '768px',
      'lg': '1024px',
      'xl': '1280px',
      '2xl': '1536px',
      'tablet': '960px',
    },
    extend: {
      width: {
        '12': '3rem',
        '95per': '95%',
      },
      height: {
        '12': '3rem',
        '95per': '95%',
      },
      maxWidth: {
        '1/5': '20%',
        '1/4': '25%',
        '1/2': '50%',
        '3/5': '60%',
        '3/4': '75%',
        '9/10': '90%',
        '95per': '95%',
        '99per': '99%',
      },
      maxHeight: {
        '1/5': '20%',
        '1/4': '25%',
        '1/2': '50%',
        '3/5': '60%',
        '3/4': '75%',
        '9/10': '90%',
        '95per': '95%',
        'full-vh': '100vh',
      },
      minWidth: {
        '1/5': '20%',
        '1/4': '25%',
        '1/2': '50%',
        '3/5': '60%',
        '3/4': '75%',
        '9/10': '90%',
        '95per': '95%',
        'full-vh': '100vh',
      },
      minHeight: {
        '1/5': '20%',
        '1/4': '25%',
        '1/2': '50%',
        '3/5': '60%',
        '3/4': '75%',
        '9/10': '90%',
        '95per': '95%',
        'full-vh': '100vh',
      },
      zIndex: {
        '11': '11',
      },
      gridTemplateColumns: {
        "two-3-1": "3fr 1fr",
      },
    },
  },
}

