import gql from 'graphql-tag';
import {createPublishedLiveFormPage, getInputDateNode, getInputDatetimeLocalNode} from '../../support/fixtures';
import {CONTENT_PATH} from '../../support/constants';
import {useFormidableSite} from './support';

const FORM_NAME = 'legacy-bounds-form';
const DATE_PATH = `${CONTENT_PATH}/${FORM_NAME}/fields/legacyDate`;
const DATETIME_PATH = `${CONTENT_PATH}/${FORM_NAME}/fields/legacyDatetime`;

const GET_MIGRATED_FIELD = gql`
	query getMigratedDateField($path: String!, $workspace: Workspace!) {
		jcr(workspace: $workspace) {
			nodeByPath(path: $path) {
				mixinTypes {
					name
				}
				minBoundMode: property(name: "fmdb:minBoundMode") {
					value
				}
				maxBoundMode: property(name: "fmdb:maxBoundMode") {
					value
				}
				min: property(name: "min") {
					value
				}
				max: property(name: "max") {
					value
				}
			}
		}
	}
`;

type MigratedFieldResponse = {
	data?: {
		jcr?: {
			nodeByPath?: {
				mixinTypes?: Array<{name: string}>;
				minBoundMode?: {value?: string} | null;
				maxBoundMode?: {value?: string} | null;
				min?: {value?: string} | null;
				max?: {value?: string} | null;
			};
		};
	};
};

const getMigratedField = (path: string, workspace: 'EDIT' | 'LIVE') =>
	cy.apollo({query: GET_MIGRATED_FIELD, variables: {path, workspace}});

/**
 * Startup migration of pre-mode date bounds: a field carrying fixed min/max values
 * but no bound modes gets stamped with mode 'date' plus the fixed-bound mixins, in
 * both workspaces, and the values stay in place. The trigger state is simulated by
 * removing the mode properties from normally-created fields — the migration is keyed
 * on exactly that observable state (mode absent, fixed value present).
 */
describe('Validation - 36 Date bounds migration', () => {
	useFormidableSite();

	it('stamps the fixed-date mode on bounds stored before the modes existed', () => {
		createPublishedLiveFormPage(
			FORM_NAME,
			'Legacy Bounds Form',
			[
				getInputDateNode({
					name: 'legacyDate',
					title: 'Legacy date',
					min: '2020-01-01T00:00:00.000',
					max: '2030-12-31T00:00:00.000'
				}),
				getInputDatetimeLocalNode({
					name: 'legacyDatetime',
					title: 'Legacy datetime',
					max: '2030-12-31T18:30:00.000'
				})
			]
		).then(() => {
			cy.executeGroovy('groovy/removeDateBoundModes.groovy', {__FIELD_PATH__: DATE_PATH})
				.then(result => cy.log(String(result)));
			cy.executeGroovy('groovy/removeDateBoundModes.groovy', {__FIELD_PATH__: DATETIME_PATH})
				.then(result => cy.log(String(result)));

			// The migration is keyed on content state and runs at module activation:
			// restarting the engine is the upgrade trigger.
			cy.executeGroovy('groovy/restartFormidableEngine.groovy', {})
				.then(result => cy.log(String(result)));

			// Module activation is asynchronous: wait until the migration stamped a mode.
			cy.waitUntil(
				() => getMigratedField(DATE_PATH, 'EDIT').then(
					(response: MigratedFieldResponse) =>
						response.data?.jcr?.nodeByPath?.minBoundMode?.value === 'date'
				),
				{timeout: 60000, interval: 2000, errorMsg: 'the migration never stamped fmdb:minBoundMode'}
			);

			(['EDIT', 'LIVE'] as const).forEach(workspace => {
				getMigratedField(DATE_PATH, workspace).then((response: MigratedFieldResponse) => {
					const node = response.data?.jcr?.nodeByPath;
					const mixins = node?.mixinTypes?.map(mixin => mixin.name) ?? [];
					const scope = `${DATE_PATH} (${workspace})`;

					expect(node?.minBoundMode?.value, scope).to.equal('date');
					expect(node?.maxBoundMode?.value, scope).to.equal('date');
					expect(mixins, scope).to.include('fmdbmix:fixedMinDate');
					expect(mixins, scope).to.include('fmdbmix:fixedMaxDate');
					expect(node?.min?.value, scope).to.contain('2020-01-01');
					expect(node?.max?.value, scope).to.contain('2030-12-31');
				});

				getMigratedField(DATETIME_PATH, workspace).then((response: MigratedFieldResponse) => {
					const node = response.data?.jcr?.nodeByPath;
					const mixins = node?.mixinTypes?.map(mixin => mixin.name) ?? [];
					const scope = `${DATETIME_PATH} (${workspace})`;

					// Only the configured side is stamped: no minimum means no min mode.
					expect(node?.minBoundMode?.value, scope).to.equal(undefined);
					expect(node?.maxBoundMode?.value, scope).to.equal('date');
					expect(mixins, scope).to.include('fmdbmix:fixedMaxDatetime');
					expect(mixins, scope).not.to.include('fmdbmix:fixedMinDatetime');
					expect(node?.max?.value, scope).to.contain('2030-12-31');
				});
			});
		});
	});
});
