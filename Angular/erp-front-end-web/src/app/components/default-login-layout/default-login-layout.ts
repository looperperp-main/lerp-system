import { Component, EventEmitter, Input, Output } from '@angular/core';
import {NgIf, NgOptimizedImage} from '@angular/common';
import {Toast} from 'primeng/toast';

@Component({
    selector: 'app-default-login-layout',
    standalone: true,
  imports: [NgOptimizedImage, NgIf, Toast],
    templateUrl: './default-login-layout.html',
    styleUrl: './default-login-layout.scss',
})
export class DefaultLoginLayout {
    @Input() title: string = '';
    @Input() primaryBtnText: string = '';
    @Input() secondaryBtnText: string = '';
    @Input() disablePrimaryBtn: boolean = true;
    @Input() showSecondaryBtn: boolean = true;
    @Output('submit') onSubmit = new EventEmitter<void>();
    @Output('navigate') onNavigate = new EventEmitter<void>();

    submit() {
      this.onSubmit.emit();
    }

    navigate() {
      this.onNavigate.emit();
    }
}
