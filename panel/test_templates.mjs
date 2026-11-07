import { readFileSync } from 'node:fs';

export function template(name = 'index') {
  return readFileSync(new URL('./templates/' + name + '.html', import.meta.url), 'utf8')
    .replace(/{% include "([\w-]+)\.html" %}/g, (_, partial) => template(partial))
    .replace(/{{ url_for\('static', filename='([^']+)'\) }}/g, '/static/$1');
}
