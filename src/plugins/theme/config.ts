import { type ModuleConfig } from '/@/cool';
import 'element-plus/theme-chalk/dark/css-vars.css';
import './static/css/index.scss';
import { useTheme } from './hooks';

export default (): ModuleConfig => {
	return {
		enable: true,
		order: 99,
		toolbar: {
			component: import('./components/theme.vue'),
			h5: false
		},
		install() {
			useTheme();
		},

		label: '主题',
		description: '身份主题、菜单分组、暗黑模式',
		author: 'COOL',
		version: '1.0.0',
		updateTime: '2024-07-22'
	};
};
