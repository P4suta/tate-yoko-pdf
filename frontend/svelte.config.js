import adapter from '@sveltejs/adapter-static';

// Hoisted regex (Biome's `useTopLevelRegex`): forcing the path separator class
// to be parsed once at module load avoids recompilation per runes() invocation.
const PATH_SEP = /[/\\]/;

/** @type {import('@sveltejs/kit').Config} */
const config = {
	compilerOptions: {
		runes: ({ filename }) => (filename.split(PATH_SEP).includes('node_modules') ? undefined : true)
	},
	kit: {
		adapter: adapter({
			fallback: 'index.html'
		})
	}
};

export default config;
